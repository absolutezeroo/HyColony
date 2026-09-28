"""Generates the DomumOrnamentum test bench sub-plugin (throwaway): one item per Domum Ornamentum block model, in
Darkwood (frame) and Lightwood (every other material), to check the shapes in game.

Run by hand, then commit the outputs (plugin/src/subplugins/DomumOrnamentum/): the build never runs it. Downloads the
DO models of source.COMMIT once into build/domum-cache/. Needs Python 3.10+, Pillow and network access.

    python tools/domum/generate.py [path/to/release-0.6.8-Assets.zip]

Left out: the architect's cutter's recipes and every recipe, connected shapes (each model file is its own static
item), open and right-hinged doors, item-only models, and models whose geometry is vanilla Minecraft's (fences,
fence gates, walls, slabs, stairs, bricks and "extra" blocks).
"""

import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "decorations"))
import source  # noqa: E402
from convert import Converter, check_atlas, model_root  # noqa: E402
from flower_pots import rounded  # noqa: E402
from icon import render  # noqa: E402
from names import item_id, lang_key, names  # noqa: E402
from pack import ROOT, Assets, save_png, validate_pack, write_json  # noqa: E402

DEFAULT_ZIP = Path.home() / ".gradle" / "caches" / "hytale-assets" / "release-0.6.8-Assets.zip"
PACK = ROOT / "plugin" / "src" / "subplugins" / "DomumOrnamentum"
MODELS = "Blocks/HyColony/DO/"
ICONS = "Icons/Items/HyColony/DO/"
PLANKS = {"dark": "BlockTextures/Wood_Darkwood_Planks.png", "light": "BlockTextures/Wood_Lightwood_Planks.png"}
DOOR_HITBOX = "HyColony_DO_Door"
LANGUAGES = {"en-US": 0, "fr-FR": 1}


def main():
    assets = Assets(Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_ZIP)
    planks = {k: assets.image("Common/" + v) for k, v in PLANKS.items()}
    wood = assets.item("Wood_Darkwood_Planks")
    for generated in ("Common", "Server"):
        shutil.rmtree(PACK / generated, ignore_errors=True)
    lines = {language: [] for language in LANGUAGES}
    counts = {}
    for folder, stem, model in source.shapes(source.fetch()):
        ident = item_id(folder, stem)
        nodes, atlas, elements = Converter(planks).convert(model)
        name = ident[len("HyColony_DO_"):]
        check_atlas(name, nodes, atlas)
        write_model(PACK / "Common" / (MODELS + name + ".blockymodel"), model_root(nodes))
        save_png(atlas, PACK / "Common" / (MODELS + name + ".png"))
        save_png(render(elements), PACK / "Common" / (ICONS + name + ".png"))
        write_json(PACK / "Server/Item/Items/HyColony/DO" / (ident + ".json"),
                   item(ident, lang_key(folder, stem), wood, folder.startswith("door")))
        for language, index in LANGUAGES.items():
            lines[language].append(lang_key(folder, stem) + " = " + names(folder, stem)[index])
        family = ident.split("_")[2]
        counts[family] = counts.get(family, 0) + 1
    write_json(PACK / "Server/Item/Block/Hitboxes/HyColony" / (DOOR_HITBOX + ".json"), door_hitbox())
    for language, entries in lines.items():
        path = PACK / "Server/Languages" / language / "hycolony.lang"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(entries) + "\n", encoding="utf-8", newline="\n")
    validate_pack(assets, PACK)
    print(json.dumps(counts, indent=1), "total", sum(counts.values()))


def item(ident, key, wood, is_door):
    """A static wood block of the model: turns to face the player like vanilla roofs (VariantRotation NESW), sounds
    and particles of the Darkwood planks, no recipe."""
    name = ident[len("HyColony_DO_"):]
    block = wood["BlockType"]
    block_type = {
        "Material": "Solid",
        "DrawType": "Model",
        "Opacity": "Transparent",
        "CustomModel": MODELS + name + ".blockymodel",
        "CustomModelTexture": [{"Texture": MODELS + name + ".png", "Weight": 1}],
        "VariantRotation": "NESW",
        "Gathering": block["Gathering"],
        "BlockParticleSetId": block["BlockParticleSetId"],
        "ParticleColor": block["ParticleColor"],
        "BlockSoundSetId": block["BlockSoundSetId"],
        "PhysicalMaterialId": block["PhysicalMaterialId"],
    }
    if is_door:
        block_type["HitboxType"] = DOOR_HITBOX
    return {
        "TranslationProperties": {"Name": "hycolony." + key},
        "Icon": ICONS + name + ".png",
        "Categories": ["Blocks.Wood"],
        "PlayerAnimationsId": "Block",
        "BlockType": block_type,
        "Tags": {"Type": ["Wood"]},
        "ItemSoundSetId": wood["ItemSoundSetId"],
    }


def door_hitbox():
    """Minecraft's closed door: 3 pixels thick along its west side, two blocks high."""
    return {"Boxes": [{"Min": {"X": 0, "Y": 0, "Z": 0}, "Max": {"X": 3 / 16, "Y": 2, "Z": 1}}]}


def write_model(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(rounded(content), separators=(",", ":"))
    path.write_text(text + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
