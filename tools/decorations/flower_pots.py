"""The flower pot and one potted state per plant, like MC's flower_pot and its potted_<plant> blocks.

Each state's model is the pot plus the plant's vanilla model scaled to stand in it (MC's flower_pot_cross parent model
does the same with the plant's texture); its texture is an atlas of the plant's texture and the pot's. Hytale takes
one model and one texture per state, hence one pair per plant. To pot a new plant, add its item id to PLANTS and
re-run generate.py.
"""

import copy
import math
import shutil

from PIL import Image

from pack import ICON_SIZE, PACK, draw_box, save_png, write_json

POT_ID = "HyColony_Flower_Pot"
MODELS = "Blocks/HyColony/Flower_Pot/"
CLAY = "BlockTextures/Clay_Smooth_Orange.png"
DIRT = "BlockTextures/Soil_Dirt_Wet.png"

# MC flower_pot model, in Hytale units (32 per block, MC pixels x 2): 12 wide, 12 high, walls 2 thick, dirt up to 8.
POT_SIZE = 12
WALL = 2
DIRT_TOP = 8
# Room left for the plant above the dirt: MC's flower_pot_cross squeezes a 16 px plant into 12 px (x 0.75).
PLANT_ROOM = 32 - DIRT_TOP
PLANT_SHRINK = 0.75
# Vanilla models that spread several plants over the whole block: squeezed to the pot's width instead.
PATCHES = {
    "Plant_Flower_Bushy_Yellow", "Plant_Flower_Common_Grey2", "Plant_Flower_Common_Lime2", "Plant_Flower_Tall_Purple",
    "Plant_Flower_Tall_Violet"
}
PATCH_ROOM = POT_SIZE + 4

# Hytale equivalents of MC's pottable plants (docs/research/carpets-flower-pots.md): one-block flowers, saplings
# (bamboo included), floor mushrooms, small ferns, dead bushes and cacti. Left out: models reaching far below the
# ground (Plant_Bush_Dead_Tall, Mushroom_Balls: Plant_Crop_Mushroom_Glowing_Orange and _Purple), which would stick
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


def generate(assets):
    clay = assets.image("Common/" + CLAY)
    dirt = assets.image("Common/" + DIRT)
    shutil.rmtree(PACK / "Common" / MODELS, ignore_errors=True)  # a plant taken off PLANTS leaves nothing behind
    write_model("Empty", pot_nodes(0), atlas(None, clay, dirt))
    states = {}
    for plant_id in PLANTS:
        states[plant_id] = potted_state(assets, plant_id, clay, dirt)
    write_json(PACK / "Server/Item/Items/HyColony" / (POT_ID + ".json"), pot_item(states))
    write_json(PACK / "Server/Item/Block/Hitboxes/HyColony" / (POT_ID + ".json"), hitbox())
    write_json(PACK / "hycolony/id-map.json", id_map())
    save_png(icon(clay, dirt), PACK / "Common/Icons/HyColony/Flower_Pot.png")


def potted_state(assets, plant_id, clay, dirt):
    """The pot's state for plant_id: its generated model and atlas, its light, and a drop of pot plus plant."""
    block = assets.item(plant_id)["BlockType"]
    texture = assets.image("Common/" + max(block["CustomModelTexture"], key=lambda t: t["Weight"])["Texture"])
    tint = block.get("Tint") or []
    if tint:
        texture = tinted(texture, tint[0])
    model = assets.json("Common/" + block["CustomModel"])
    plant = scaled(model["nodes"], block.get("CustomModelScale", 1), PATCH_ROOM if plant_id in PATCHES else PLANT_ROOM)
    write_model(plant_id, pot_nodes(texture.size[1]) + [plant], atlas(texture, clay, dirt))
    state = {
        "CustomModel": MODELS + plant_id + ".blockymodel",
        "CustomModelTexture": [{"Texture": MODELS + plant_id + ".png", "Weight": 1}],
        "Gathering": {"Soft": {"DropList": {"Container": {"Type": "Multiple", "Containers": [
            {"Type": "Single", "Item": {"ItemId": POT_ID}},
            {"Type": "Single", "Item": {"ItemId": plant_id}},
        ]}}}},
    }
    if "Light" in block:
        state["Light"] = block["Light"]
    return state


def tinted(texture, colour):
    """texture multiplied by colour ('#rrggbb'), as the client tints the vanilla block."""
    rgb = tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))
    r, g, b, a = texture.split()
    channels = [c.point(lambda p, k=k: p * k // 255) for c, k in zip((r, g, b), rgb)]
    return Image.merge("RGBA", (*channels, a))


def atlas(plant_texture, clay, dirt):
    """The plant's texture at (0, 0), so its UVs stay as they are, then the clay and the dirt below it."""
    plant_height = plant_texture.size[1] if plant_texture else 0
    width = power_of_two(max(plant_texture.size[0] if plant_texture else 0, 64))
    image = Image.new("RGBA", (width, power_of_two(plant_height + 32)), (0, 0, 0, 0))
    if plant_texture:
        image.paste(plant_texture, (0, 0))
    image.paste(clay.crop((0, 0, 32, 32)), (0, plant_height))
    image.paste(dirt.crop((0, 0, 32, 32)), (32, plant_height))
    return image


def power_of_two(n):
    return 1 << (n - 1).bit_length()


def pot_nodes(clay_row):
    """The four walls and the dirt, textured from the atlas row clay_row (clay at x 0, dirt at x 32)."""
    half = POT_SIZE / 2
    inner = POT_SIZE - 2 * WALL
    wall_middle = half - WALL / 2
    boxes = [
        ("Wall_North", (0, half, -wall_middle), (POT_SIZE, POT_SIZE, WALL), 0),
        ("Wall_South", (0, half, wall_middle), (POT_SIZE, POT_SIZE, WALL), 0),
        ("Wall_West", (-wall_middle, half, 0), (WALL, POT_SIZE, inner), 0),
        ("Wall_East", (wall_middle, half, 0), (WALL, POT_SIZE, inner), 0),
        ("Dirt", (0, DIRT_TOP / 2, 0), (inner, DIRT_TOP, inner), 32),
    ]
    return [box_node(name, centre, size, (u, clay_row)) for name, centre, size, u in boxes]


def box_node(name, centre, size, uv):
    face = {"offset": {"x": uv[0], "y": uv[1]}, "mirror": {"x": False, "y": False}, "angle": 0}
    return node(name, [0, 0, 0], {
        "type": "box",
        "offset": xyz(centre),
        "stretch": xyz((1, 1, 1)),
        "settings": {"size": xyz(size)},
        "visible": True,
        "doubleSided": False,
        "shadingMode": "flat",
        "unwrapMode": "custom",
        "textureLayout": {side: copy.deepcopy(face) for side in ("front", "back", "left", "right", "top", "bottom")},
    })


def node(name, position, shape, children=()):
    return {"id": "0", "name": name, "children": list(children), "position": xyz(position),
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1}, "shape": shape}


def empty_shape():
    return {"type": "none", "offset": xyz((0, 0, 0)), "stretch": xyz((1, 1, 1)), "settings": {"isPiece": False},
            "visible": True, "doubleSided": False, "shadingMode": "flat", "unwrapMode": "custom",
            "textureLayout": {}}


def xyz(values):
    return {"x": values[0], "y": values[1], "z": values[2]}


def scaled(nodes, vanilla_scale, width_room):
    """The plant's nodes under a group standing on the dirt, uniformly scaled to fit the room above it."""
    low, high = bounds(nodes)
    height = max(high[1], 1.0)
    width = 2 * max(abs(low[0]), abs(high[0]), abs(low[2]), abs(high[2]), 1.0)
    factor = min(PLANT_SHRINK * vanilla_scale, PLANT_ROOM / height, width_room / width)
    if low[1] < 0:
        factor = min(factor, DIRT_TOP / -low[1])
    plant = [copy.deepcopy(n) for n in nodes]
    for n in walk(plant):
        n["position"] = {k: v * factor for k, v in n["position"].items()}
        shape = n["shape"]
        shape["offset"] = {k: v * factor for k, v in shape["offset"].items()}
        shape["stretch"] = {k: v * factor for k, v in shape["stretch"].items()}
    return node("Plant", (0, DIRT_TOP, 0), empty_shape(), plant)


def walk(nodes):
    for n in nodes:
        yield n
        yield from walk(n.get("children", []))


def bounds(nodes):
    """Lowest and highest corner, in model units, of every shape of the model."""
    points = []
    for n in nodes:
        collect(n, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0, 1.0), points)
    if not points:
        return (0, 0, 0), (0, 0, 0)
    return tuple(min(p[i] for p in points) for i in range(3)), tuple(max(p[i] for p in points) for i in range(3))


def collect(n, parent_position, parent_rotation, points):
    o = n["orientation"]
    rotation = multiply(parent_rotation, (o["x"], o["y"], o["z"], o["w"]))
    p = n["position"]
    position = add(parent_position, rotate(parent_rotation, (p["x"], p["y"], p["z"])))
    shape = n["shape"]
    offset = (shape["offset"]["x"], shape["offset"]["y"], shape["offset"]["z"])
    stretch = (shape["stretch"]["x"], shape["stretch"]["y"], shape["stretch"]["z"])
    for corner in corners(shape):
        local = add(offset, tuple(c * s for c, s in zip(corner, stretch)))
        points.append(add(position, rotate(rotation, local)))
    for child in n.get("children", []):
        collect(child, position, rotation, points)


def corners(shape):
    size = shape.get("settings", {}).get("size")
    if shape["type"] == "box":
        hx, hy, hz = size["x"] / 2, size["y"] / 2, size["z"] / 2
        return [(sx * hx, sy * hy, sz * hz) for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]
    if shape["type"] == "quad":
        hx, hy = size["x"] / 2, size["y"] / 2
        if shape["settings"].get("normal", "+Z").endswith("Y"):
            return [(sx * hx, 0, sy * hy) for sx in (-1, 1) for sy in (-1, 1)]
        return [(sx * hx, sy * hy, 0) for sx in (-1, 1) for sy in (-1, 1)]
    return []


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def multiply(q, r):
    x1, y1, z1, w1 = q
    x2, y2, z2, w2 = r
    return (w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2)


def rotate(q, v):
    norm = math.sqrt(sum(c * c for c in q)) or 1.0
    q = tuple(c / norm for c in q)
    x, y, z, _ = multiply(multiply(q, (v[0], v[1], v[2], 0.0)), (-q[0], -q[1], -q[2], q[3]))
    return (x, y, z)


def write_model(name, nodes, texture):
    root = node("Origin", (0, 0, 0), empty_shape(), nodes)
    for index, n in enumerate(walk([root]), start=1):
        n["id"] = str(index)
    write_json(PACK / "Common" / (MODELS + name + ".blockymodel"), {"lod": "auto", "nodes": [root]})
    save_png(texture, PACK / "Common" / (MODELS + name + ".png"))


def pot_item(states):
    """MC flower pot: 3 bricks, 3/8 high, needs no support (Java places it even over the void), breaks at once."""
    return {
        "TranslationProperties": {"Name": "hycolony.item.flower_pot.name"},
        "Icon": "Icons/HyColony/Flower_Pot.png",
        "Categories": ["Blocks.Deco"],
        "Recipe": {
            "Input": [{"ItemId": "Soil_Clay_Brick", "Quantity": 3}],
            "BenchRequirement": [{"Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"]}],
        },
        "PlayerAnimationsId": "Block",
        "BlockType": {
            "Material": "Solid",
            "DrawType": "Model",
            "Opacity": "Transparent",
            "CustomModel": MODELS + "Empty.blockymodel",
            "CustomModelTexture": [{"Texture": MODELS + "Empty.png", "Weight": 1}],
            "HitboxType": POT_ID,
            "Gathering": {"Soft": {"ItemId": POT_ID}},
            # UseBlockEvent fires only for an interaction type the block declares (UseBlockInteraction):
            # Use is the interaction key, Secondary the right click with a block item such as a plant.
            "Interactions": {
                "Use": {"Interactions": [{"Type": "Simple"}]},
                "Secondary": {"Interactions": [{"Type": "Simple"}]},
            },
            "State": {"Definitions": states},
            "BlockParticleSetId": "Clay",
            "ParticleColor": "#b4643c",
            "BlockSoundSetId": "Clay_Pot_Small",
            "PhysicalMaterialId": "Stone",
        },
        "Tags": {"Type": ["Furniture"]},
        "ItemSoundSetId": "ISS_Blocks_Wood",
    }


def hitbox():
    """MC flower pot shape: 6 x 6 pixels, 6 high, centred."""
    low, high = 5 / 16, 11 / 16
    return {"Boxes": [{"Min": {"X": low, "Y": 0, "Z": low}, "Max": {"X": high, "Y": 6 / 16, "Z": high}}]}


def id_map():
    """The pot, and plant item -> potted block key (Hytale names a state '*<block>_State_Definitions_<state>')."""
    return {
        "blocks": {"decorations.flower_pot": POT_ID},
        "flowerPots": {p: "*" + POT_ID + "_State_Definitions_" + p for p in PLANTS},
    }


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
