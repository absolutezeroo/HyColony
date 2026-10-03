"""Minecraft's 1x2 bed, one per wool colour (spec 2026-09-29 HyVanilla beds): one shared model built in Blockbench
(docs/research/hytale-models.md), one texture painted per colour with brushes (brushes.py: cloth in its wool's colour
for the blanket, in white wool's for the sheet and pillow, wood in softwood planks' for the frame) in layers and lit
from the model (catalog.module_texture), the hitbox, the icons, the recipe from wool and planks, and the recolouring
recipes that follow each wool's own recipe. Deviation from MC: a four-post wooden frame around the mattress (asked by
the user, to suit Hytale's furniture); our own geometry."""

import json
from types import SimpleNamespace

from PIL import Image

from brushes import average, cloth, wood
from catalog import module_surface, module_texture, surveyed
from conditions import DRY_INTERIOR, MAINTAINED
from icons import ICON_SIZE, draw_model
from models import bounds, walk
from pack import save_png, write_json
from paths import PACK

# Hand-built in Blockbench (Hytale Prop, 32 units per block), not generated: 1 x 2 blocks, the head on the origin cell
# (towards -z, as Hytale's own beds), the blanket's top at Minecraft's 9/16. One UV island per face (bake.light_map).
MODEL = "Blocks/HyVanilla/Bed.blockymodel"
PLANKS = "BlockTextures/Wood_Softwood_Planks_Top.png"
SHEET = "BlockTextures/Cloth_White.png"
# Node name prefixes of each hitbox part.
PARTS = {"head": ("Post_Head", "Post_Cap_Head", "Headboard"), "foot": ("Post_Foot", "Post_Cap_Foot", "Footboard"),
         "body": ("Frame", "Mattress", "Blanket"), "pillow": ("Pillow",)}


def bed_id(colour):
    return "HyVanilla_Bed_" + colour


def texture_path(colour):
    return "Blocks/HyVanilla/Bed/" + colour + ".png"


def generate(assets, colours):
    """Writes the hitbox, and per colour the texture, icon, item and recolouring recipe."""
    nodes = json.loads((PACK / "Common" / MODEL).read_text(encoding="utf-8"))["nodes"]
    write_json(PACK / "Server/Item/Block/Hitboxes/HyVanilla/HyVanilla_Bed.json", hitbox(nodes))
    wooden = wood(average(assets.image("Common/" + PLANKS)))
    sheet = cloth(average(assets.image("Common/" + SHEET)))
    values, contexts = surveyed(LOOK, nodes)
    for old in (PACK / "Server/Item/Recipes/HyVanilla").glob("HyVanilla_Bed_*.json"):
        old.unlink()
    for colour in colours:
        wool_item = assets.item("Cloth_Block_Wool_" + colour)
        wool = cloth(average(assets.image("Common/BlockTextures/Cloth_" + colour + ".png")))
        look = SimpleNamespace(**vars(LOOK), tiles=lambda _, wool=wool: {"wood": wooden, "sheet": sheet, "wool": wool})
        image = module_texture(look, nodes, assets, values, module_surface(look, contexts))
        save_png(image, PACK / "Common" / texture_path(colour))
        save_png(icon(nodes, image), PACK / "Common/Icons/Items/HyVanilla" / ("Bed_" + colour + ".png"))
        write_json(PACK / "Server/Item/Items/HyVanilla" / (bed_id(colour) + ".json"), bed_item(colour, wool_item))
        recolour = recolour_recipe(colour, wool_item.get("Recipe"), colours)
        if recolour is not None:
            write_json(PACK / "Server/Item/Recipes/HyVanilla" / (bed_id(colour) + "_Dye.json"), recolour)


def material(name, _side):
    """The material a model node is painted with, from its name."""
    if name.startswith(PARTS["head"] + PARTS["foot"] + ("Frame",)):
        return "wood"
    return "wool" if name == "Blanket" else "sheet"


# Painted in layers (spec 2026-10-03 blockpaint surfaces, catalog.module_texture): a bed slept in and kept, indoors,
# standing on the floor; its tiles are each colour's (generate).
LOOK = SimpleNamespace(GROUNDED=True, PICTURES=frozenset(), CONDITION=MAINTAINED, ENVIRONMENT=DRY_INTERIOR, SEED=11,
                       material=material)


def hitbox(nodes):
    """One box per part (head end, foot end, mattress, pillow), each the part's model bounds standing on the floor;
    model x and z run from -16, the origin cell's corner. Fails when a part matches no node (renamed in Blockbench)."""
    boxes = []
    for part, prefixes in PARTS.items():
        matched = [n for n in walk(nodes) if n["name"].startswith(prefixes)]
        if not matched:
            raise SystemExit(f"bed hitbox: no model node named {prefixes} for the {part}")
        low, high = bounds(matched)
        boxes.append({"Min": {"X": (low[0] + 16) / 32, "Y": 0, "Z": (low[2] + 16) / 32},
                      "Max": {"X": (high[0] + 16) / 32, "Y": high[1] / 32, "Z": (high[2] + 16) / 32}})
    return {"Boxes": boxes}


def bed_item(colour, wool_item):
    """Hytale's own bed behaviour (Furniture_Village_Bed: Block_Bed, respawn point) in Minecraft's shape; 3 wool of
    its colour and 3 planks of any wood give one (MC <color>_bed)."""
    item_id = bed_id(colour)
    wool = wool_item["BlockType"]
    return {
        "TranslationProperties": {"Name": "hyvanilla.item.bed." + colour.lower() + ".name"},
        "Icon": "Icons/Items/HyVanilla/Bed_" + colour + ".png",
        "Categories": ["Furniture.Beds"],
        "Recipe": {
            "Input": [{"ItemId": "Cloth_Block_Wool_" + colour, "Quantity": 3},
                      {"ResourceTypeId": "Wood_Planks", "Quantity": 3}],
            "BenchRequirement": [{"Type": "Crafting", "Id": "Furniture_Bench", "Categories": ["Furniture_Beds"]}],
        },
        "PlayerAnimationsId": "Block",
        "BlockType": {
            "Material": "Solid",
            "DrawType": "Model",
            "Opacity": "Transparent",
            "CustomModel": MODEL,
            "CustomModelTexture": [{"Texture": texture_path(colour), "Weight": 1}],
            "HitboxType": "HyVanilla_Bed",
            "VariantRotation": "NESW",
            "Gathering": {"Breaking": {"GatherType": "SoftBlocks", "ItemId": item_id}},
            # First value, to tune in game, after Furniture_Crude_Bed (Hytale's only 1 x 2 bed, same shift towards the
            # foot as the others): just above the mattress; offsets count from the origin cell's centre.
            "Beds": [{"Offset": {"X": -0.1, "Y": 0.1, "Z": 0.8}, "Yaw": 0}],
            "Interactions": {"Use": "Block_Bed", "Primary": "Check_Can_Break_Respawn"},
            "Support": {"Down": [{"FaceType": "Full"}]},
            "BlockEntity": {"Components": {"RespawnBlock": {}}},
            "BlockParticleSetId": "Dust",
            "ParticleColor": wool["ParticleColor"],
            "BlockSoundSetId": "Cloth",
            "PhysicalMaterialId": "Wool",
            "TextureComputedColor": wool["TextureComputedColor"],
        },
        "Tags": {"Type": ["Furniture"]},
        "ItemSoundSetId": "ISS_Items_Cloth",
        "MaxStack": 1,
    }


def recolour_recipe(colour, wool_recipe, colours):
    """The wool's own recipe with each wool swapped for the bed of that colour (white bed + red petal -> red bed);
    None for a wool without a wool-based recipe (white). Deviation from MC: Hytale has no dye, its wool is recoloured
    with petals, so a bed follows its wool's path."""
    if not wool_recipe:
        return None
    inputs, swapped = [], False
    for material in wool_recipe["Input"]:
        item = material.get("ItemId", "")
        wool_colour = item.removeprefix("Cloth_Block_Wool_")
        if item.startswith("Cloth_Block_Wool_") and wool_colour in colours:
            inputs.append({"ItemId": bed_id(wool_colour), "Quantity": material["Quantity"]})
            swapped = True
        else:
            inputs.append(dict(material))
    if not swapped:
        return None
    output = {"ItemId": bed_id(colour), "Quantity": 1}
    return {
        "Input": inputs,
        "Output": [output],
        "PrimaryOutput": output,
        "BenchRequirement": [{"Type": "Crafting", "Id": "Furniture_Bench", "Categories": ["Furniture_Beds"]}],
    }


def icon(nodes, texture):
    """The bed in perspective, head at the back, drawn from its model and painted texture."""
    image = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    draw_model(image, nodes, texture, 0.7, (41.7, 36))
    return image
