"""Minecraft's 1x2 bed, one per wool colour (spec 2026-09-29 HyVanilla beds): one shared model, one generated texture
per colour (its wool, white wool for the pillow, softwood planks for the frame), the hitbox, the icons, the recipe
from wool and planks, and the recolouring recipes that follow each wool's own recipe. Deviation from MC: a wooden
headboard and footboard around the mattress (asked by the user, to suit Hytale's furniture); our own geometry."""

import json

from PIL import Image

from models import box_node, empty_shape, face_rects, node, walk
from pack import ICON_SIZE, PACK, draw_box, save_png, write_json

MODEL = "Blocks/HyVanilla/Bed.blockymodel"
PLANKS = "BlockTextures/Wood_Softwood_Planks_Top.png"
PILLOW = "BlockTextures/Cloth_White.png"
CELL = 32
# Model in units (32 per block), 1 x 2 blocks, the head on the origin cell (towards -z, as Hytale's own beds): a plank
# base under the mattress, whose top stays at Minecraft's 9/16, between a headboard and a lower footboard.
BASE_LOW, MATTRESS_LOW, MATTRESS_HIGH = 4, 8, 18
BOARD = 4
HEADBOARD_HIGH, FOOTBOARD_HIGH = 28, 22
HEAD_Z, FOOT_Z = -16, 48
INNER_HEAD_Z, INNER_FOOT_Z = HEAD_Z + BOARD, FOOT_Z - BOARD
SHEET_LENGTH = 20
# The raised pillow on the white sheet: 24 wide, 4 high, 12 long, 2 units off the headboard.
PILLOW_SIZE = (24, 4, 12)
PILLOW_GAP = 2
# Texture layout (pixels): the wool and the planks each tiled 2 x 2 (the cover reads up to 36 x 44, the base 32 x 56),
# and the white wool.
WOOL_UV, WHITE_UV, PLANKS_UV = (0, 0), (64, 0), (0, 64)
# Node name prefix -> the texture region (u0, v0, u1, v1) its faces must stay in: beyond it they read the neighbour.
REGIONS = {"Pillow": (64, 0, 96, 32), "Sheet": (64, 0, 96, 32), "Cover": (0, 0, 64, 64), "Base": (0, 64, 64, 128),
           "Headboard": (0, 64, 64, 128), "Footboard": (0, 64, 64, 128)}
TEXTURE_SIZE = (128, 128)


def bed_id(colour):
    return "HyVanilla_Bed_" + colour


def texture_path(colour):
    return "Blocks/HyVanilla/Bed/" + colour + ".png"


def generate(assets, colours):
    """Writes the model, hitbox, and per colour the texture, icon, item and recolouring recipe."""
    write_model()
    write_json(PACK / "Server/Item/Block/Hitboxes/HyVanilla/HyVanilla_Bed.json", hitbox())
    planks = cell(assets.image("Common/" + PLANKS))
    pillow = cell(assets.image("Common/" + PILLOW))
    for old in (PACK / "Server/Item/Recipes/HyVanilla").glob("HyVanilla_Bed_*.json"):
        old.unlink()
    for colour in colours:
        wool_item = assets.item("Cloth_Block_Wool_" + colour)
        wool = cell(assets.image("Common/BlockTextures/Cloth_" + colour + ".png"))
        save_png(texture(wool, pillow, planks), PACK / "Common" / texture_path(colour))
        save_png(icon(wool, pillow, planks), PACK / "Common/Icons/Items/HyVanilla" / ("Bed_" + colour + ".png"))
        write_json(PACK / "Server/Item/Items/HyVanilla" / (bed_id(colour) + ".json"), bed_item(colour, wool_item))
        recolour = recolour_recipe(colour, wool_item.get("Recipe"), colours)
        if recolour is not None:
            write_json(PACK / "Server/Item/Recipes/HyVanilla" / (bed_id(colour) + "_Dye.json"), recolour)


def cell(image):
    return image.crop((0, 0, CELL, CELL))


def texture(wool, pillow, planks):
    image = Image.new("RGBA", TEXTURE_SIZE, (0, 0, 0, 0))
    for dx in (0, CELL):
        for dy in (0, CELL):
            image.paste(wool, (WOOL_UV[0] + dx, WOOL_UV[1] + dy))
            image.paste(planks, (PLANKS_UV[0] + dx, PLANKS_UV[1] + dy))
    image.paste(pillow, WHITE_UV)
    return image


def model_nodes():
    """Headboard and footboard, the plank base between them, on it the white sheet then the cover, and the raised
    pillow on the sheet."""
    height = MATTRESS_HIGH - MATTRESS_LOW
    middle_y = (MATTRESS_LOW + MATTRESS_HIGH) / 2
    sheet_end = INNER_HEAD_Z + SHEET_LENGTH
    inner = INNER_FOOT_Z - INNER_HEAD_Z
    return [
        box_node("Headboard", (0, HEADBOARD_HIGH / 2, HEAD_Z + BOARD / 2), (32, HEADBOARD_HIGH, BOARD), PLANKS_UV),
        box_node("Footboard", (0, FOOTBOARD_HIGH / 2, FOOT_Z - BOARD / 2), (32, FOOTBOARD_HIGH, BOARD), PLANKS_UV),
        box_node("Base", (0, (BASE_LOW + MATTRESS_LOW) / 2, (INNER_HEAD_Z + INNER_FOOT_Z) / 2),
                 (32, MATTRESS_LOW - BASE_LOW, inner), PLANKS_UV),
        box_node("Sheet", (0, middle_y, (INNER_HEAD_Z + sheet_end) / 2), (32, height, SHEET_LENGTH), WHITE_UV),
        box_node("Cover", (0, middle_y, (sheet_end + INNER_FOOT_Z) / 2), (32, height, INNER_FOOT_Z - sheet_end),
                 WOOL_UV),
        box_node("Pillow", (0, MATTRESS_HIGH + PILLOW_SIZE[1] / 2, pillow_start() + PILLOW_SIZE[2] / 2), PILLOW_SIZE,
                 WHITE_UV),
    ]


def pillow_start():
    return INNER_HEAD_Z + PILLOW_GAP


def write_model():
    nodes = model_nodes()
    for name, u0, v0, u1, v1 in face_rects(nodes):
        low_u, low_v, high_u, high_v = next(r for prefix, r in REGIONS.items() if name.startswith(prefix))
        if u0 < low_u or v0 < low_v or u1 > high_u or v1 > high_v:
            raise SystemExit(f"bed {name}: face reads ({u0}, {v0})-({u1}, {v1}) outside its texture region")
    root = node("Origin", (0, 0, 0), empty_shape(), nodes)
    for index, n in enumerate(walk([root]), start=1):
        n["id"] = str(index)
    path = PACK / "Common" / MODEL
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"lod": "auto", "nodes": [root]}, indent=2) + "\n", encoding="utf-8", newline="\n")


def hitbox():
    """The mattress over the origin cell and the next one along +z, 9/16 high, the two boards and the pillow."""
    def box(z0, z1, high):
        return {"Min": {"X": 0, "Y": 0, "Z": z0}, "Max": {"X": 1, "Y": high / 32, "Z": z1}}

    return {"Boxes": [box(0, 2, MATTRESS_HIGH), box(0, BOARD / 32, HEADBOARD_HIGH),
                      box(2 - BOARD / 32, 2, FOOTBOARD_HIGH), pillow_box()]}


def pillow_box():
    half = PILLOW_SIZE[0] / 2
    low_z = pillow_start() + 16
    top = (MATTRESS_HIGH + PILLOW_SIZE[1]) / 32
    return {"Min": {"X": (16 - half) / 32, "Y": 0, "Z": low_z / 32},
            "Max": {"X": (16 + half) / 32, "Y": top, "Z": (low_z + PILLOW_SIZE[2]) / 32}}


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


def icon(wool, pillow, planks):
    """The bed in perspective, head at the back: headboard, base, mattress with its pillow, then footboard."""
    image = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    scale, origin = 0.7, (41.7, 36)
    wood = {"top": planks, "front": planks, "right": planks}
    draw_box(image, ((-16, 0, HEAD_Z), (16, HEADBOARD_HIGH, INNER_HEAD_Z)), wood, scale, origin)
    draw_box(image, ((-16, BASE_LOW, INNER_HEAD_Z), (16, MATTRESS_LOW, INNER_FOOT_Z)), wood, scale, origin)
    length, height = INNER_FOOT_Z - INNER_HEAD_Z, MATTRESS_HIGH - MATTRESS_LOW
    top = Image.new("RGBA", (32, length))
    for y in range(0, length, CELL):
        top.paste(wool, (0, y))
    top.paste(pillow.crop((0, 0, 32, SHEET_LENGTH)), (0, 0))
    side = Image.new("RGBA", (length, height))
    for x in range(0, length, CELL):
        side.paste(wool.crop((0, 0, CELL, height)), (x, 0))
    side.paste(pillow.crop((0, 0, SHEET_LENGTH, height)), (length - SHEET_LENGTH, 0))
    draw_box(image, ((-16, MATTRESS_LOW, INNER_HEAD_Z), (16, MATTRESS_HIGH, INNER_FOOT_Z)),
             {"top": top, "front": wool.crop((0, 0, 32, height)), "right": side}, scale, origin)
    width, high, long = PILLOW_SIZE
    cushion = {"top": pillow.crop((0, 0, width, long)), "front": pillow.crop((0, 0, width, high)),
               "right": pillow.crop((0, 0, long, high))}
    draw_box(image, ((-width / 2, MATTRESS_HIGH, pillow_start()), (width / 2, MATTRESS_HIGH + high,
             pillow_start() + long)), cushion, scale, origin)
    draw_box(image, ((-16, 0, INNER_FOOT_Z), (16, FOOTBOARD_HIGH, FOOT_Z)), wood, scale, origin)
    return image
