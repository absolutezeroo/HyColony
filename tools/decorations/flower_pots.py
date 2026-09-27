"""The flower pots, one per smooth clay colour, and one potted state per plant, like Minecraft's flower_pot and its
potted_<plant> blocks. The colours are a requested addition: Minecraft's flower pot has one colour.

Each state's model is the pot plus the plant's vanilla model scaled to stand in it (Minecraft's flower_pot_cross parent
model does the same with the plant's texture). The models are shared by every colour; each colour has one atlas with
the same layout (every plant texture, the dirt, and its clay), used by the pot and all its states. To pot a new plant,
add its item id to PLANTS and re-run generate.py.
"""

import shutil

from PIL import Image

from models import bounds, box_node, empty_shape, face_rects, node, scaled, shift_uvs, walk
from pack import ICON_SIZE, PACK, draw_box, save_png, write_json

MODELS = "Blocks/HyColony/Flower_Pot/"
DIRT = "BlockTextures/Soil_Dirt_Wet.png"
# Hytale's dyed clay blocks (Soil_Clay_Smooth_<C>, the counterpart of Minecraft's terracotta): pot colour ->
# (English, French) name, after the clay's vanilla name. Three of that clay make one pot.
COLOURS = {
    "Black": ("Black", "noir"), "Blue": ("Blue", "bleu"), "Cyan": ("Cyan", "cyan"), "Green": ("Green", "vert"),
    "GreenDark": ("Dark Green", "vert foncé"), "Grey": ("Dark Gray", "gris foncé"), "Grey2": ("Gray", "gris"),
    "Lime": ("Lime", "vert citron"), "Orange": ("Brown", "marron"), "Pink": ("Pink", "rose"),
    "Purple": ("Purple", "violet"), "Red": ("Dark Brown", "brun foncé"), "Red2": ("Red", "rouge"),
    "White": ("White", "blanc"), "Yellow": ("Yellow", "jaune"), "Yellow2": ("Orange", "orange"),
}
CELL = 32
ATLAS_WIDTH = 512

# Minecraft flower_pot model, in Hytale units (32 per block, Minecraft pixels x 2): 12 wide, 12 high, walls 2 thick,
# dirt up to 8.
POT_SIZE = 12
WALL = 2
DIRT_TOP = 8
# Room left for the plant above the dirt: flower_pot_cross squeezes a 16 px plant into 12 px (x 0.75).
PLANT_ROOM = 32 - DIRT_TOP
PLANT_SHRINK = 0.75
# Vanilla models that spread several plants over the whole block: squeezed to the pot's width instead.
PATCHES = {
    "Plant_Flower_Bushy_Yellow", "Plant_Flower_Common_Grey2", "Plant_Flower_Common_Lime2", "Plant_Flower_Tall_Purple",
    "Plant_Flower_Tall_Violet",
}
PATCH_ROOM = POT_SIZE + 4

# Hytale equivalents of Minecraft's pottable plants (docs/research/carpets-flower-pots.md): one-block flowers,
# saplings (bamboo included), floor mushrooms, small ferns, dead bushes and cacti. Left out: models reaching far below
# the ground (Plant_Bush_Dead_Tall, Mushroom_Balls: Plant_Crop_Mushroom_Glowing_Orange and _Purple), which would stick
# out under the pot.
PLANTS = [
    "Plant_Flower_Bushy_Blue", "Plant_Flower_Bushy_Cyan", "Plant_Flower_Bushy_Green", "Plant_Flower_Bushy_Grey",
    "Plant_Flower_Bushy_Orange", "Plant_Flower_Bushy_Poisoned", "Plant_Flower_Bushy_Purple", "Plant_Flower_Bushy_Red",
    "Plant_Flower_Bushy_Violet", "Plant_Flower_Bushy_White", "Plant_Flower_Bushy_Yellow",
    "Plant_Flower_Common_Blue", "Plant_Flower_Common_Blue2", "Plant_Flower_Common_Cyan", "Plant_Flower_Common_Cyan2",
    "Plant_Flower_Common_Grey", "Plant_Flower_Common_Grey2", "Plant_Flower_Common_Lime", "Plant_Flower_Common_Lime2",
    "Plant_Flower_Common_Orange", "Plant_Flower_Common_Orange2", "Plant_Flower_Common_Pink",
    "Plant_Flower_Common_Pink2", "Plant_Flower_Common_Poisoned", "Plant_Flower_Common_Poisoned2",
    "Plant_Flower_Common_Purple", "Plant_Flower_Common_Purple2", "Plant_Flower_Common_Red", "Plant_Flower_Common_Red2",
    "Plant_Flower_Common_Violet", "Plant_Flower_Common_Violet2", "Plant_Flower_Common_White",
    "Plant_Flower_Common_White2", "Plant_Flower_Common_Yellow", "Plant_Flower_Common_Yellow2",
    "Plant_Flower_Flax_Blue", "Plant_Flower_Flax_Orange", "Plant_Flower_Flax_Pink", "Plant_Flower_Flax_Purple",
    "Plant_Flower_Flax_White", "Plant_Flower_Flax_Yellow", "Plant_Flower_Hemlock",
    "Plant_Flower_Orchid_Blue", "Plant_Flower_Orchid_Cyan", "Plant_Flower_Orchid_Orange", "Plant_Flower_Orchid_Pink",
    "Plant_Flower_Orchid_Poisoned", "Plant_Flower_Orchid_Purple", "Plant_Flower_Orchid_Red",
    "Plant_Flower_Orchid_White", "Plant_Flower_Orchid_Yellow", "Plant_Flower_Poisoned_Orange",
    "Plant_Flower_Tall_Blue", "Plant_Flower_Tall_Cyan", "Plant_Flower_Tall_Cyan2", "Plant_Flower_Tall_Pink",
    "Plant_Flower_Tall_Purple", "Plant_Flower_Tall_Red", "Plant_Flower_Tall_Violet", "Plant_Flower_Tall_Yellow",
    "Plant_Sapling_Amber", "Plant_Sapling_Apple", "Plant_Sapling_Ash", "Plant_Sapling_Aspen", "Plant_Sapling_Azure",
    "Plant_Sapling_Bamboo", "Plant_Sapling_Banyan", "Plant_Sapling_Beech", "Plant_Sapling_Birch",
    "Plant_Sapling_Bottletree", "Plant_Sapling_Camphor", "Plant_Sapling_Cedar", "Plant_Sapling_Crystal",
    "Plant_Sapling_Dry", "Plant_Sapling_Fig_Blue", "Plant_Sapling_Fire", "Plant_Sapling_Gumboab", "Plant_Sapling_Ice",
    "Plant_Sapling_Jungle", "Plant_Sapling_Maple", "Plant_Sapling_Oak", "Plant_Sapling_Palm", "Plant_Sapling_Palo",
    "Plant_Sapling_Petrified", "Plant_Sapling_Poisoned", "Plant_Sapling_Redwood", "Plant_Sapling_Sallow",
    "Plant_Sapling_Spiral", "Plant_Sapling_Spruce", "Plant_Sapling_Spruce_Frozen", "Plant_Sapling_Stormbark",
    "Plant_Sapling_Windwillow", "Plant_Sapling_Wisteria_Wild",
    "Plant_Crop_Mushroom_Boomshroom_Small", "Plant_Crop_Mushroom_Cap_Brown", "Plant_Crop_Mushroom_Cap_Green",
    "Plant_Crop_Mushroom_Cap_Poison", "Plant_Crop_Mushroom_Cap_Red", "Plant_Crop_Mushroom_Cap_White",
    "Plant_Crop_Mushroom_Common_Blue", "Plant_Crop_Mushroom_Common_Brown", "Plant_Crop_Mushroom_Common_Lime",
    "Plant_Crop_Mushroom_Flatcap_Blue", "Plant_Crop_Mushroom_Flatcap_Green", "Plant_Crop_Mushroom_Glowing_Blue",
    "Plant_Crop_Mushroom_Glowing_Green", "Plant_Crop_Mushroom_Glowing_Red", "Plant_Crop_Mushroom_Glowing_Violet",
    "Plant_Fern", "Plant_Fern_Arid", "Plant_Fern_Tall",
    "Plant_Bush_Dead", "Plant_Bush_Dead_Twisted",
    "Plant_Cactus_1", "Plant_Cactus_2", "Plant_Cactus_3", "Plant_Cactus_Ball_1", "Plant_Cactus_Flat_1",
    "Plant_Cactus_Flat_2", "Plant_Cactus_Flat_3", "Plant_Cactus_Flower",
]


def pot_id(colour):
    return "HyColony_Flower_Pot_" + colour


def generate(assets):
    shutil.rmtree(PACK / "Common" / MODELS, ignore_errors=True)  # a plant taken off PLANTS leaves nothing behind
    for old in (PACK / "Server/Item/Items/HyColony").glob("HyColony_Flower_Pot*.json"):
        old.unlink()
    plants = {p: assets.item(p)["BlockType"] for p in PLANTS}
    textures = {p: plant_texture(assets, block) for p, block in plants.items()}
    layout, size = pack_layout(dict(textures.values()))
    base = Image.new("RGBA", size, (0, 0, 0, 0))
    for key, image in dict(textures.values()).items():
        base.paste(image, layout[key])
    dirt = assets.image("Common/" + DIRT)
    base.paste(dirt.crop((0, 0, CELL, CELL)), layout["dirt"])
    write_model("Empty", pot_nodes(layout["clay"], layout["dirt"]))
    for plant_id, block in plants.items():
        key, image = textures[plant_id]
        model = assets.json("Common/" + block["CustomModel"])
        check_uvs(plant_id, model["nodes"], image.size)
        shift_uvs(model["nodes"], *layout[key])
        room = PATCH_ROOM if plant_id in PATCHES else PLANT_ROOM
        plant = scaled(model["nodes"], fit(model["nodes"], block.get("CustomModelScale", 1), room), DIRT_TOP)
        write_model(plant_id, pot_nodes(layout["clay"], layout["dirt"]) + [plant])
    for colour in COLOURS:
        clay_item = assets.item("Soil_Clay_Smooth_" + colour)
        clay = clay_texture(assets, clay_item)
        atlas = base.copy()
        atlas.paste(clay.crop((0, 0, CELL, CELL)), layout["clay"])
        save_png(atlas, PACK / "Common" / (MODELS + "Atlas_" + colour + ".png"))
        write_json(PACK / "Server/Item/Items/HyColony" / (pot_id(colour) + ".json"), pot_item(colour, clay_item, plants))
        save_png(icon(clay, dirt), PACK / "Common/Icons/Items/HyColony" / ("Flower_Pot_" + colour + ".png"))
    write_json(PACK / "Server/Item/Block/Hitboxes/HyColony/HyColony_Flower_Pot.json", hitbox())
    write_json(PACK / "hycolony/id-map.json", id_map())


def plant_texture(assets, block):
    """(key, image): the plant's most weighted texture, its static Tint baked in as the client would tint it."""
    path = max(block["CustomModelTexture"], key=lambda t: t["Weight"])["Texture"]
    tint = (block.get("Tint") or [None])[0]
    image = assets.image("Common/" + path)
    return (path + "|" + str(tint), tinted(image, tint) if tint else image)


def clay_texture(assets, clay_item):
    textures = clay_item["BlockType"]["Textures"][0]
    return assets.image("Common/" + (textures.get("All") or textures.get("Sides")))


def tinted(texture, colour):
    """texture multiplied by colour ('#rrggbb')."""
    rgb = tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))
    r, g, b, a = texture.split()
    channels = [c.point(lambda p, k=k: p * k // 255) for c, k in zip((r, g, b), rgb)]
    return Image.merge("RGBA", (*channels, a))


def pack_layout(images):
    """Places every texture of images ({key: image}) plus a 'clay' and a 'dirt' cell on a grid of 32 px cells, tallest
    first, ATLAS_WIDTH wide. Returns {key: (u, v)} and the atlas size, a power of two high. Same input, same layout."""
    sizes = {key: (image.size[0] // CELL, image.size[1] // CELL) for key, image in images.items()}
    order = sorted(sizes, key=lambda k: (-sizes[k][1], -sizes[k][0], k)) + ["clay", "dirt"]
    sizes["clay"] = sizes["dirt"] = (1, 1)
    used = set()
    layout = {}
    for key in order:
        w, h = sizes[key]
        row = 0
        while (column := free_column(used, row, w, h)) is None:
            row += 1
        used |= {(column + dx, row + dy) for dx in range(w) for dy in range(h)}
        layout[key] = (column * CELL, row * CELL)
    height = max(y for _, y in used) + 1
    return layout, (ATLAS_WIDTH, power_of_two(height * CELL))


def free_column(used, row, w, h):
    for x in range(ATLAS_WIDTH // CELL - w + 1):
        if all((x + dx, row + dy) not in used for dx in range(w) for dy in range(h)):
            return x
    return None


def power_of_two(n):
    return 1 << (n - 1).bit_length()


def check_uvs(plant_id, nodes, size):
    """Fails on a face reading outside its texture: in an atlas it would read the neighbouring texture."""
    for name, u0, v0, u1, v1 in face_rects(nodes):
        if u0 < 0 or v0 < 0 or u1 > size[0] or v1 > size[1]:
            raise SystemExit(f"{plant_id}: face of {name} reads ({u0}, {v0})-({u1}, {v1}) outside its {size} texture")


def fit(nodes, vanilla_scale, width_room):
    """Uniform scale making the plant stand in the room above the dirt, never sinking below the pot."""
    low, high = bounds(nodes)
    height = max(high[1], 1.0)
    width = 2 * max(abs(low[0]), abs(high[0]), abs(low[2]), abs(high[2]), 1.0)
    factor = min(PLANT_SHRINK * vanilla_scale, PLANT_ROOM / height, width_room / width)
    return min(factor, DIRT_TOP / -low[1]) if low[1] < 0 else factor


def pot_nodes(clay_uv, dirt_uv):
    """The four walls (clay) and the dirt."""
    half = POT_SIZE / 2
    inner = POT_SIZE - 2 * WALL
    wall_middle = half - WALL / 2
    return [
        box_node("Wall_North", (0, half, -wall_middle), (POT_SIZE, POT_SIZE, WALL), clay_uv),
        box_node("Wall_South", (0, half, wall_middle), (POT_SIZE, POT_SIZE, WALL), clay_uv),
        box_node("Wall_West", (-wall_middle, half, 0), (WALL, POT_SIZE, inner), clay_uv),
        box_node("Wall_East", (wall_middle, half, 0), (WALL, POT_SIZE, inner), clay_uv),
        box_node("Dirt", (0, DIRT_TOP / 2, 0), (inner, DIRT_TOP, inner), dirt_uv),
    ]


def write_model(name, nodes):
    root = node("Origin", (0, 0, 0), empty_shape(), nodes)
    for index, n in enumerate(walk([root]), start=1):
        n["id"] = str(index)
    write_json(PACK / "Common" / (MODELS + name + ".blockymodel"), {"lod": "auto", "nodes": [root]})


def pot_item(colour, clay_item, plants):
    """Minecraft flower pot: 3 clay (its bricks), 3/8 high, needs no support (Java places it even over the void),
    breaks at once; one state per plant, which drops the pot and the plant."""
    pot = pot_id(colour)
    texture = [{"Texture": MODELS + "Atlas_" + colour + ".png", "Weight": 1}]
    states = {}
    for plant_id, block in plants.items():
        state = {
            "CustomModel": MODELS + plant_id + ".blockymodel",
            "CustomModelTexture": texture,
            "Gathering": {"Soft": {"DropList": {"Container": {"Type": "Multiple", "Containers": [
                {"Type": "Single", "Item": {"ItemId": pot}},
                {"Type": "Single", "Item": {"ItemId": plant_id}},
            ]}}}},
        }
        if "Light" in block:
            state["Light"] = block["Light"]
        states[plant_id] = state
    return {
        "TranslationProperties": {"Name": "hycolony.item.flower_pot." + colour.lower() + ".name"},
        "Icon": "Icons/Items/HyColony/Flower_Pot_" + colour + ".png",
        "Categories": ["Blocks.Deco"],
        "Recipe": {
            "Input": [{"ItemId": "Soil_Clay_Smooth_" + colour, "Quantity": 3}],
            "BenchRequirement": [{"Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"]}],
        },
        "PlayerAnimationsId": "Block",
        "BlockType": {
            "Material": "Solid",
            "DrawType": "Model",
            "Opacity": "Transparent",
            "CustomModel": MODELS + "Empty.blockymodel",
            "CustomModelTexture": texture,
            "HitboxType": "HyColony_Flower_Pot",
            "Gathering": {"Soft": {"ItemId": pot}},
            # UseBlockEvent fires only for an interaction type the block declares (UseBlockInteraction):
            # Use is the interaction key, Secondary the right click with a block item such as a plant.
            "Interactions": {
                "Use": {"Interactions": [{"Type": "Simple"}]},
                "Secondary": {"Interactions": [{"Type": "Simple"}]},
            },
            "State": {"Definitions": states},
            "BlockParticleSetId": "Clay",
            "ParticleColor": clay_item["BlockType"]["ParticleColor"],
            "BlockSoundSetId": "Clay_Pot_Small",
            "PhysicalMaterialId": "Stone",
        },
        "Tags": {"Type": ["Furniture"]},
        "ItemSoundSetId": "ISS_Blocks_Wood",
    }


def hitbox():
    """Minecraft flower pot shape: 6 x 6 pixels, 6 high, centred."""
    low, high = 5 / 16, 11 / 16
    return {"Boxes": [{"Min": {"X": low, "Y": 0, "Z": low}, "Max": {"X": high, "Y": 6 / 16, "Z": high}}]}


def id_map():
    """Pot block -> plant item -> that pot's block holding it (Hytale names a state
    '*<block>_State_Definitions_<state>')."""
    return {"flowerPots": {
        pot_id(c): {p: "*" + pot_id(c) + "_State_Definitions_" + p for p in PLANTS} for c in COLOURS
    }}


def icon(clay, dirt):
    image = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    top = clay.crop((0, 0, POT_SIZE, POT_SIZE))
    inner = POT_SIZE - 2 * WALL
    soil = dirt.crop((0, 0, inner, inner))
    soil = Image.merge("RGBA", (*soil.convert("RGB").point(lambda p: p * 3 // 4).split(), soil.getchannel("A")))
    top.paste(soil, (WALL, WALL))
    side = clay.crop((0, 0, POT_SIZE, POT_SIZE))
    half = POT_SIZE / 2
    draw_box(image, ((-half, 0, -half), (half, POT_SIZE, half)), {"top": top, "front": side, "right": side}, 2.4,
             (32, 46))
    return image
