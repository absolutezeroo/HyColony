"""The architect's cutter block (MC DO ArchitectsCutterBlock): a bench opening the plugin's cutter window, which
crafts with the player's own materials as Hytale's benches do (so it holds no container). It wears the vanilla builder's bench, Hytale's own architect bench.

Deviation from MC: DO's recipe (1 iron ingot, 3 stone slabs, 3 logs; DO-gen recipes/architectscutter.json) is made
at Hytale's Workbench, logs as any trunk (Wood_Trunk resource type)."""

from blocks import common
from pack import write_json

IDENT = "HyColony_DO_ArchitectsCutter"
NAME_KEY = "item.do.architectscutter.name"
NAMES = {"en-US": "Architect's cutter", "fr-FR": "Établi de l'architecte"}
# The vanilla bench keys the cutter keeps: its look, collision, rotation, sounds and how it breaks.
BENCH_KEYS = ("Material", "DrawType", "Opacity", "CustomModel", "CustomModelTexture", "HitboxType", "VariantRotation",
              "Gathering", "BlockParticleSetId", "BlockSoundSetId", "PhysicalMaterialId", "Support")


def generate(ctx):
    """Writes the cutter item and its names, and records the builder bench's window sounds for the id-map."""
    bench = ctx.assets.item("Bench_Builders")
    block = {key: bench["BlockType"][key] for key in BENCH_KEYS}
    # The window is the plugin's, so it plays the bench's own open and close sounds itself (id-map sounds).
    config = bench["BlockType"]["Bench"]
    ctx.sounds["cutter.open"] = config["LocalOpenSoundEventId"]
    ctx.sounds["cutter.close"] = config["LocalCloseSoundEventId"]
    # A no-op Use: the plugin's CutterSystem opens the window.
    block["Interactions"] = {"Use": {"Interactions": [{"Type": "Simple"}]}}
    item = {
        "TranslationProperties": {"Name": "hycolony." + NAME_KEY},
        "Icon": bench["Icon"],
        "IconProperties": bench["IconProperties"],
        "Categories": ["Furniture.Benches"],
        "PlayerAnimationsId": "Block",
        "MaxStack": 1,
        "ItemSoundSetId": bench.get("ItemSoundSetId", "ISS_Blocks_Wood"),
        "Recipe": {
            "Input": [{"ItemId": "Ingredient_Bar_Iron", "Quantity": 1}, {"ItemId": "Rock_Stone_Half", "Quantity": 3},
                      {"ResourceTypeId": "Wood_Trunk", "Quantity": 3}],
            "BenchRequirement": [{"Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"]}],
        },
        "BlockType": block,
    }
    write_json(ctx.pack / common.ITEMS / (IDENT + ".json"), item)
    for language, name in NAMES.items():
        ctx.lang[language].append(f"{NAME_KEY} = {name}")
